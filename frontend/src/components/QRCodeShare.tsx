import { QRCodeSVG } from 'qrcode.react';
import { Copy, Check, Share2 } from 'lucide-react';
import { useState } from 'react';
import './QRCodeShare.css';

interface QRCodeShareProps {
    url: string;
    tournamentName: string;
}

export function QRCodeShare({ url, tournamentName }: QRCodeShareProps) {
    const [copied, setCopied] = useState(false);

    const handleCopy = async () => {
        try {
            await navigator.clipboard.writeText(url);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        } catch (err) {
            console.error('Failed to copy:', err);
        }
    };

    const handleShare = async () => {
        if (typeof navigator.share === 'function') {
            try {
                await navigator.share({
                    title: `${tournamentName} - Live Scores`,
                    text: 'Watch the live tournament scores!',
                    url: url,
                });
            } catch (err) {
                // User cancelled or error
                console.error('Share failed:', err);
            }
        } else {
            handleCopy();
        }
    };

    return (
        <div className="qr-share-container">
            <div className="qr-header">
                <Share2 size={20} />
                <span>Share Live Scores</span>
            </div>

            <div className="qr-code-wrapper">
                <QRCodeSVG
                    value={url}
                    size={180}
                    level="H"
                    bgColor="transparent"
                    fgColor="#1e293b"
                    includeMargin={false}
                />
            </div>

            <p className="qr-instructions">
                Scan to view live tournament updates
            </p>

            <div className="share-url-box">
                <span className="url-text">{url}</span>
            </div>

            <div className="share-actions">
                <button className="copy-btn" onClick={handleCopy}>
                    {copied ? <Check size={16} /> : <Copy size={16} />}
                    {copied ? 'Copied!' : 'Copy Link'}
                </button>

                {typeof navigator.share === 'function' && (
                    <button className="native-share-btn" onClick={handleShare}>
                        <Share2 size={16} />
                        Share
                    </button>
                )}
            </div>

            <p className="viewer-note">
                📺 Viewers don't need to login - scores update automatically!
            </p>
        </div>
    );
}
